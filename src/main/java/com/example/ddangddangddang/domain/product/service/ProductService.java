package com.example.ddangddangddang.domain.product.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.exception.CategoryErrorCode;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.dto.request.ProductImageUploadRequest;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductImageUploadResponse;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.exception.ProductErrorCode;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
@Transactional
public class ProductService {
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AuctionRepository auctionRepository;
    private final ProductImageResolver imageResolver;
    private final FileStorage fileStorage;

    //상품 상세 조회(삭제된 상품은 제외)
    @Transactional(readOnly = true)
    public ProductResponse findAddBy(Long productsId) {
        Product product = productRepository.findByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(
                () -> new BusinessException(ProductErrorCode.NOT_FOUND));

        return ProductResponse.from(product, fileStorage::publicUrl);

    }

    //내 상품 조회(삭제된 상품은 제외) + 상품 마다 경매 상태
    @Transactional(readOnly = true)
    public Page<ProductResponse> myProducts(Long sellerId, Pageable pageable) {
        Page<Product> products = productRepository.findBySellerIdAndStatusNot(sellerId, Status.DELETED, pageable);

        //이번 페이지 상품들의 id만 모으기
        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();

        //상품별 최근 경매 : 상품마다 최신 경매 id 조회 ->그 id 경매만 조회
        HashMap<Long, Auction> latestAuctions = new HashMap<>();
        if (!productIds.isEmpty()){
            List<Long> latestIds = auctionRepository.findLatestIdsByProductIds(productIds);
            for (Auction auction : auctionRepository.findAllById(latestIds)){
                latestAuctions.put(auction.getProduct().getId(),auction);
            }
        }
        return products.map(product -> ProductResponse.from(product, latestAuctions.get(product.getId()), fileStorage::publicUrl));
    }

    //상품 이미지 업로드 URL(갤러리·설명 속 이미지 공용). 올린 뒤 키를 상품 등록·수정 요청에 넣는다.
    @Transactional(readOnly = true)
    public ProductImageUploadResponse createImageUpload(Long sellerId, ProductImageUploadRequest request) {
        return imageResolver.createUpload(sellerId, request.contentType(), request.contentLength());
    }

    //상품 생성(하위 카테고리 내에서만 생성(대분류에는 등록X))
    public ProductResponse creation(Long sellerId, ProductRequest request) {
        User seller = userRepository.findById(sellerId).orElseThrow(
                () -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        Category category = findCategory(request.categoryId());
        // 새 상품이라 저장된 이미지가 없다. 모두 내 키인지, 올라갔는지 확인한다.
        Set<String> none = Set.of();
        List<String> imageKeys = imageResolver.resolveImages(sellerId, none,
                request.imageKeys() == null ? List.of() : request.imageKeys());
        imageResolver.checkDescription(sellerId, none, request.description());
        Product product = new Product(seller, category, request.name(), request.description());
        product.changeImages(imageKeys);
        Product save = productRepository.save(product);

        return ProductResponse.from(save, fileStorage::publicUrl);

    }

    //상품 수정(본인만 수정가능+보내지 않은 값들은 기존 유지)
    public ProductResponse updateProduct(Long sellerId, Long productsId, UpdateRequest request) {
        Product product = productRepository.findWithLockByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(
                () -> new BusinessException(ProductErrorCode.NOT_FOUND));

        if (!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_OWNER);
        }

        // 진행 OR 대기 중 경매 있을시
        if (auctionRepository.existsByProductIdAndStatusIn(product.getId(), List.of(AuctionStatus.READY,AuctionStatus.ONGOING))){
            throw new BusinessException(ProductErrorCode.IN_AUCTION);
        }

        Category category = (request.categoryId() != null)
                ? findCategory(request.categoryId())
                : product.getCategory();

        String name = (request.name() == null || request.name().isBlank())
                ? product.getName()
                : request.name();
        String description = (request.description() != null)
                ? request.description()
                : product.getDescription();

        // 지금 쓰는 이미지(갤러리 + 설명)는 다시 확인하지 않는다. 판매자가 순서만 바꾸거나 다른 상품 정보만 고칠 수 있게.
        Set<String> before = ProductImageResolver.usedKeys(product.getImageKeys(), product.getDescription());
        List<String> imageKeys = request.imageKeys() == null ? null
                : imageResolver.resolveImages(sellerId, before, request.imageKeys());
        imageResolver.checkDescription(sellerId, before, description);

        product.update(category, name, description);
        if (imageKeys != null) {
            product.changeImages(imageKeys);
        }
        // 판매된 상품은 주문이 대표 이미지 주소를 그대로 들고 있어 빠진 이미지도 지우지 않는다.
        if (product.getStatus() != Status.SOLD) {
            imageResolver.deleteRemovedImagesAfterCommit(before,
                    ProductImageResolver.usedKeys(product.getImageKeys(), product.getDescription()));
        }

        return ProductResponse.from(product, fileStorage::publicUrl);

    }
    //카테고리 조회시 하위가 있을시 대분류에 생성 수정시 예외처리
    private Category findCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> new BusinessException(CategoryErrorCode.NOT_FOUND)
        );

        if (category.getParent() == null && categoryRepository.existsByParentId(categoryId)) {
            throw new BusinessException(ProductErrorCode.SUBCATEGORY_REQUIRED);
        }

        return category;
    }

    //상품 삭제(본인 상품만 삭제 가능+삭제시 Status를 DELETED 전환)
    public void delete(Long sellerId, Long productsId) {
        Product product = productRepository.findWithLockByIdAndStatusNot(productsId, Status.DELETED).orElseThrow(
                () -> new BusinessException(ProductErrorCode.NOT_FOUND));
        if (!product.getSeller().getId().equals(sellerId)) {
            throw new BusinessException(ProductErrorCode.NOT_OWNER);
        }
        // 진행 OR 대기 중 경매 있을시
        if (auctionRepository.existsByProductIdAndStatusIn(product.getId(), List.of(AuctionStatus.READY,AuctionStatus.ONGOING))){
            throw new BusinessException(ProductErrorCode.IN_AUCTION);
        }
        product.delete();
    }
}
